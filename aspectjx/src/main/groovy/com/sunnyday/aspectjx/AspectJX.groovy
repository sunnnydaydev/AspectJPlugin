package com.sunnyday.aspectjx

import org.aspectj.bridge.IMessage
import org.aspectj.bridge.MessageHandler
import org.aspectj.tools.ajc.Main
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile


/**
 * 自定义插件类：集成AspectJ的功能。哪个module集成我们的插件时，此project就代表哪个module 的project对象
 * */
class AspectJX implements Plugin<Project> {

    @Override
    void apply(Project project) {
        // 哪个工程使用本插件时，就动态给哪个工程添加aspectj依赖。
        project.dependencies {
            implementation 'org.aspectj:aspectjrt:1.8.9'//aspectj 依赖
        }
        // 固定代码，直接粘贴来即可。
        final def log = project.logger
        final def variants = project.android.applicationVariants
        variants.all { variant ->
            if (!variant.buildType.isDebuggable()) {
                log.debug("Skipping non-debuggable build type '${variant.buildType.name}'.")
                return
            }

            JavaCompile javaCompile = variant.javaCompile
            javaCompile.doLast {
                String[] args = ["-showWeaveInfo",
                                 "-1.8",
                                 "-inpath", javaCompile.destinationDir.toString(),
                                 "-aspectpath", javaCompile.classpath.asPath,
                                 "-d", javaCompile.destinationDir.toString(),
                                 "-classpath", javaCompile.classpath.asPath,
                                 "-bootclasspath", project.android.bootClasspath.join(File.pathSeparator)]
                log.debug "ajc args: " + Arrays.toString(args)

                MessageHandler handler = new MessageHandler(true)
                new Main().run(args, handler)
                for (IMessage message : handler.getMessages(null, true)) {
                    switch (message.getKind()) {
                        case IMessage.ABORT:
                        case IMessage.ERROR:
                        case IMessage.FAIL:
                            log.error message.message, message.thrown
                            break
                        case IMessage.WARNING:
                            log.warn message.message, message.thrown
                            break
                        case IMessage.INFO:
                            log.info message.message, message.thrown
                            break
                        case IMessage.DEBUG:
                            log.debug message.message, message.thrown
                            break
                    }
                }
            }

        }
    }
}