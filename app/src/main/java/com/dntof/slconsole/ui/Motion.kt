// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/**
 * 全局动效参数（Material 3 emphasized 曲线）。
 * 时长会自动乘以系统「动画时长缩放」，开发者选项里关掉动画时直接跳到结束状态。
 */
object Motion {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard = FastOutSlowInEasing

    const val SHORT = 150
    const val MEDIUM = 300
    const val LONG = 350

    private val TAB_ROUTES = setOf(Routes.DASHBOARD, Routes.PLAYERS, Routes.CONSOLE, Routes.MAPS, Routes.MORE)

    private fun NavBackStackEntry.isTab() = destination.route in TAB_ROUTES

    /** 底栏之间切换用 fade through；进入二级页面从右侧滑入一小段并淡入。 */
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (initialState.isTab() && targetState.isTab()) {
            fadeIn(tween(220, delayMillis = 70, easing = EmphasizedDecelerate)) +
                scaleIn(tween(220, delayMillis = 70, easing = EmphasizedDecelerate), initialScale = 0.98f)
        } else {
            slideInHorizontally(tween(LONG, easing = EmphasizedDecelerate)) { it / 8 } +
                fadeIn(tween(MEDIUM, easing = EmphasizedDecelerate))
        }
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (initialState.isTab() && targetState.isTab()) {
            fadeOut(tween(90, easing = EmphasizedAccelerate))
        } else {
            slideOutHorizontally(tween(LONG, easing = EmphasizedAccelerate)) { -it / 16 } +
                fadeOut(tween(200, easing = EmphasizedAccelerate))
        }
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (initialState.isTab() && targetState.isTab()) {
            fadeIn(tween(220, delayMillis = 70, easing = EmphasizedDecelerate))
        } else {
            slideInHorizontally(tween(LONG, easing = EmphasizedDecelerate)) { -it / 16 } +
                fadeIn(tween(MEDIUM, easing = EmphasizedDecelerate))
        }
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (initialState.isTab() && targetState.isTab()) {
            fadeOut(tween(90, easing = EmphasizedAccelerate))
        } else {
            slideOutHorizontally(tween(250, easing = EmphasizedAccelerate)) { it / 8 } +
                fadeOut(tween(200, easing = EmphasizedAccelerate))
        }
    }
}
