import { MENU_EVENT, PREVIEW_ENTITY, PREVIEW_TAG, SKINS } from './catalog.js';
const OWNER_KEY = 'tierb:owner';
const propertyKey = (playerId) => `tierb:preview.${playerId}`;
/** Only touches the custom preview entity. No player-skin API exists or is called here. */
export function installPreviewAddon({ world, system, ActionFormData, FormCancelationReason }) {
    const inFlight = new Set();
    function removePreview(playerId) {
        const key = propertyKey(playerId);
        const id = world.getDynamicProperty(key);
        if (typeof id !== 'string')
            return;
        try {
            const entity = world.getEntity(id);
            if (entity?.isValid() && entity.typeId === PREVIEW_ENTITY && entity.hasTag(PREVIEW_TAG) && entity.getDynamicProperty(OWNER_KEY) === playerId)
                entity.remove();
        }
        finally {
            world.setDynamicProperty(key, undefined);
        }
    }
    function previewLocation(player) {
        const loc = player.location;
        const view = player.getViewDirection();
        const length = Math.hypot(view.x, view.z);
        const dx = length > 0.001 ? view.x / length : 0;
        const dz = length > 0.001 ? view.z / length : 1;
        const x = Math.floor(loc.x + dx * 2) + 0.5;
        const y = Math.floor(loc.y);
        const z = Math.floor(loc.z + dz * 2) + 0.5;
        const feet = player.dimension.getBlock({ x: Math.floor(x), y, z: Math.floor(z) });
        const head = player.dimension.getBlock({ x: Math.floor(x), y: y + 1, z: Math.floor(z) });
        if (!feet?.isAir || !head?.isAir)
            throw new Error('Stand in a clear area with two air blocks in front of you.');
        return { x, y, z, dx: loc.x - x, dz: loc.z - z };
    }
    function selectPreview(player, skin) {
        const loc = previewLocation(player);
        removePreview(player.id);
        const entity = player.dimension.spawnEntity(PREVIEW_ENTITY, loc);
        try {
            entity.setDynamicProperty(OWNER_KEY, player.id);
            entity.addTag(PREVIEW_TAG);
            entity.triggerEvent(skin.event);
            const yaw = Math.atan2(-loc.dx, loc.dz) * 180 / Math.PI;
            entity.setRotation({ x: 0, y: yaw });
            entity.nameTag = `PREVIEW ONLY | ${skin.name}`;
            world.setDynamicProperty(propertyKey(player.id), entity.id);
        }
        catch (error) {
            if (entity.isValid())
                entity.remove();
            throw error;
        }
    }
    async function openMenu(player) {
        if (!player.isValid())
            return;
        const playerId = player.id;
        if (inFlight.has(playerId))
            return;
        inFlight.add(playerId);
        try {
            const form = new ActionFormData()
                .title('Tier B | PREVIEW ONLY')
                .body('Choose a mannequin texture. This does NOT equip a skin on your player. Your actual player skin stays unchanged. The last button removes the mannequin.');
            for (const skin of SKINS)
                form.button(skin.name, skin.icon);
            form.button('Remove preview');
            const response = await form.show(player);
            if (!player.isValid()) {
                removePreview(playerId);
                return;
            }
            if (response.canceled) {
                if (response.cancelationReason === FormCancelationReason.UserBusy)
                    player.sendMessage('Preview menu was busy. Close chat/other menus and run /scriptevent tierb:preview again.');
                return;
            }
            const selection = response.selection;
            if (selection === SKINS.length) {
                removePreview(playerId);
                player.sendMessage('Preview removed. Your player skin was not changed.');
                return;
            }
            if (selection === undefined || !Number.isInteger(selection) || selection < 0 || selection >= SKINS.length)
                return;
            const skin = SKINS[selection];
            selectPreview(player, skin);
            player.sendMessage(`Preview entity: ${skin.name}. This is NOT your player skin; your player skin was not changed.`);
        }
        catch (error) {
            if (player.isValid())
                player.sendMessage(`Preview failed: ${error instanceof Error ? error.message : String(error)}. Check that both Tier B packs are active.`);
        }
        finally {
            inFlight.delete(playerId);
        }
    }
    system.afterEvents.scriptEventReceive.subscribe((event) => {
        if (event.id !== MENU_EVENT)
            return;
        const source = event.sourceEntity;
        if (!source || source.typeId !== 'minecraft:player')
            return;
        const player = source;
        system.run(() => { void openMenu(player); });
    });
    world.afterEvents.playerLeave.subscribe((event) => {
        inFlight.delete(event.playerId);
        removePreview(event.playerId);
    });
}
