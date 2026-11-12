import { system, world } from '@minecraft/server';
import { ActionFormData, FormCancelationReason } from '@minecraft/server-ui';
import { installPreviewAddon } from './controller.js';

installPreviewAddon({ world, system, ActionFormData, FormCancelationReason });
