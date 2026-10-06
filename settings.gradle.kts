// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

// 本地调试想用阿里云镜像加速时，在 ~/.gradle/gradle.properties 写 slconsole.aliyunMirror=true。
// 任务名里带 Release 的构建会忽略这项，始终先解析 Google 与 Maven Central。
// pluginManagement 单独编译，下面两处判断要保持一致。

pluginManagement {
    val requested = settings.providers.gradleProperty("slconsole.aliyunMirror").orNull
        ?: settings.startParameter.projectProperties["slconsole.aliyunMirror"]
    val useAliyun = requested.equals("true", ignoreCase = true) &&
        settings.startParameter.taskNames.none { it.contains("Release", ignoreCase = true) }
    repositories {
        if (useAliyun) {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val requested = settings.providers.gradleProperty("slconsole.aliyunMirror").orNull
        ?: settings.startParameter.projectProperties["slconsole.aliyunMirror"]
    val useAliyun = requested.equals("true", ignoreCase = true) &&
        settings.startParameter.taskNames.none { it.contains("Release", ignoreCase = true) }
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useAliyun) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "SLPocketConsole"
include(":app")
