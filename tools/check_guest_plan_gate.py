#!/usr/bin/env python3
"""Static guard: guests must never enter wallet or subscription-plan UI."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "android/app/src/main/java/com/addonstation/mobile/ui"


def read(relative: str) -> str:
    return (UI / relative).read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"guest plan gate: {message}")


def balanced_block(source: str, marker: str) -> str:
    start = source.find(marker)
    require(start >= 0, f"missing marker {marker!r}")
    opening = source.find("{", start)
    require(opening >= 0, f"missing opening brace after {marker!r}")
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise SystemExit(f"guest plan gate: unclosed block after {marker!r}")


def main() -> None:
    settings = read("settings/SettingsScreen.kt")
    notifications = read("settings/NotificationSettingsScreen.kt")
    home = read("home/HomeScreen.kt")
    nav = read("nav/AppNav.kt")
    wallet_surfaces = {
        "settings top bar": read("components/AuthSettingsUi.kt"),
        "pixel top bar": read("components/PixelTopBar.kt"),
        "store header": read("store/StoreScreens.kt"),
    }

    # Settings remains available to a guest, so every plan-specific element in
    # it needs its own explicit signed-in branch.
    require(
        re.search(r"if\s*\(signedIn\)\s*\{\s*SettingsPlanCard\(", settings) is not None,
        "the Settings subscription card is not guarded by signedIn",
    )
    profile_card = balanced_block(settings, "private fun SettingsProfileCard(")
    require(
        re.search(r"if\s*\(signedIn\)\s*\{[\s\S]*?planLabelOf\(", profile_card) is not None,
        "the guest Settings profile card still exposes a plan badge",
    )
    require(
        re.search(
            r"if\s*\(signedIn\)\s*\{\s*AuthSettingsDivider\(\)\s*val canShowOnline[\s\S]*?R\.string\.plan_locked_online",
            settings,
        ) is not None,
        "the plan-locked online preference is visible before sign-in",
    )
    require(
        re.search(
            r"if\s*\(signedIn\)\s*\{\s*AuthNotificationToggleRow\([\s\S]*?R\.string\.plan_title",
            notifications,
        ) is not None,
        "the subscription notification category is visible before sign-in",
    )

    quick_actions = balanced_block(home, "private fun HomeQuickActions(")
    require(
        re.search(r"private fun HomeQuickActions\(\s*signedIn: Boolean,", home) is not None,
        "Home quick actions do not receive auth state",
    )
    require(
        re.search(r"if\s*\(signedIn\)\s*\{[\s\S]*?Icons\.Filled\.WorkspacePremium", quick_actions) is not None,
        "the Home upgrade action is visible before sign-in",
    )

    # Internal clicks are redirected immediately; route-level gates remain the
    # final barrier for state restoration and any direct navigation attempt.
    require(
        "if (!signedIn && (isChatRoute(route) || isPlanRoute(route) || isWalletRoute(route)))" in nav,
        "wallet/plan links are not redirected to authentication before navigation",
    )
    plan_route = balanced_block(nav, "composable(Routes.PLANS) {")
    checkout_start = nav.find("route = Routes.CHECKOUT,")
    checkout_end = nav.find("composable(Routes.ORDERS) {", checkout_start)
    require(checkout_start >= 0 and checkout_end > checkout_start, "checkout route block is missing")
    checkout_route = nav[checkout_start:checkout_end]
    orders_route = balanced_block(nav, "composable(Routes.ORDERS) {")
    wallet_route = balanced_block(nav, "composable(Routes.WALLET) {")
    history_route = balanced_block(nav, "composable(Routes.AC_HISTORY) {")
    for label, block, screen in (
        ("plans", plan_route, "PlansScreen("),
        ("checkout", checkout_route, "CheckoutScreen("),
        ("orders", orders_route, "MyOrdersScreen("),
        ("wallet", wallet_route, "AcWalletScreen("),
        ("AC history", history_route, "AcTransactionHistoryScreen("),
    ):
        require("SignInGate(" in block, f"the direct {label} route has no SignInGate")
        require(block.index("SignInGate(") < block.index(screen),
                f"the {label} screen is composed before its SignInGate")

    for label, surface in wallet_surfaces.items():
        require(
            '(walletBalance ?: 0).toString()' in surface,
            f"{label} does not show 0 while the AC balance is unavailable",
        )
        require(
            'walletBalance?.toString() ?: "—"' not in surface,
            f"{label} brought back the misleading dash AC balance",
        )

    print("guest plan/wallet gate: OK")


if __name__ == "__main__":
    main()
