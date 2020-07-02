# AspectJPlugin
### 自定义gradle插件集成AspectJ功能，以及结合全埋点实战

### 实现

> 自定义插件的知识已学过了，在这里写下详细步骤有点浪费时间啦，直接给出插件的核心代码吧！！！

###### 1、build.gradle

```java
apply plugin: 'groovy'
apply plugin: 'java-library'
apply plugin: 'maven'

dependencies {
    //Gradle Plugin 依赖
    implementation gradleApi()
    //本地发布 Plugin
    implementation localGroovy()
    //aspectj
    implementation 'org.aspectj:aspectjtools:1.8.9'
    implementation 'org.aspectj:aspectjweaver:1.8.9'
}

repositories {
    mavenCentral()
}

//本地发布，发布到根目录的 /repo 文件夹下
uploadArchives{
    repositories {
        mavenDeployer{
            //本地仓库路径。放到项目根目录下repo文件夹下
            repository(url :uri('../repo'))
            pom.groupId = "com.sunnyday.aspectjx"
            pom.artifactId = "aspectjx"
            pom.version = '1.0.0'
        }
    }
}

```

> 其实也没啥东西，就是比之前自定义gradle插件练习中多了两个aspectj 依赖代码。引入aspectJ工具以及织入工具类库而已。

###### 2、插件类

```java
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
```

> 主要就是aspectj 依赖动态引入，以及这一大坨重复代码粘贴到我们的自定义插件中。其实这个插件的内容会动态的加载到使用插件的build.gradle 中。因为build.gradle 就对应module 的project对象。

###### 3、结合全埋点实战

（1）原理

> 对于Android系统中的View，它的点击处理逻辑，都是通过设置相应的listener对象并重写相应的回调方法实现的。比如，对于Button、ImageView等控件，它设置的listener对象均是android.view.View.OnClickListener类型，然后重写它的onClick(android.view.View)回调方法。我们只要利用一定的技术原理，在应用程序编译期间（比如生成.dex之前），在其onClick(android.view.View)方法中插入相应的埋点代码，即可做到自动埋点，也就是全埋点。
>
> 核心实现：基于aspectJ 拦截 android.view.View.OnClickListener.onClick(android.view.View)。在方法执行前后插入代码即可。

（2）伪代码

```java
/**
 * Create by SunnyDay on 21:45 2020/07/01
 */
@Aspect
public class HookViewClick {
    @Around("execution(* android.view.View.OnClickListener.onClick(..))")
    public void around(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        Log.d(TAG, "点击事件之前：触发埋点操作...");
        proceedingJoinPoint.proceed();
        Log.d(TAG, "点击事件之后...");
    }
}
// 效果：
2020-04-01 03:09:06.255 20208-20208/com.sunnyday.aspectjplugin D/MainActivity: 点击事件之前：触发埋点操作...
2020-04-01 03:09:06.256 20208-20208/com.sunnyday.aspectjplugin I/MainActivity: onClick: 
2020-04-01 03:09:06.256 20208-20208/com.sunnyday.aspectjplugin D/MainActivity: 点击事件之后...
```

> 这里埋点操作我用log代替了，成了伪代码。为啥这样做呢？因为日志收集的逻辑是重复了，我们在安卓全埋点appViewScreen中就写过一套了代码相同，再写没意义啦用log代替吧！！！！

### 一些坑：无法采集的事件情况

> - 通过ButterKnife的@OnClick注解绑定的事件
> - 通过android：OnClick属性绑定的事件
> - MenuItem的点击事件
> - 设置的OnClickListener使用了Lambda语法。

### 采坑：解决方案

###### 1、针对ButterKnife解决方案

> 由于ButterKnife是通过@OnClick注解绑定点击事件的，再加上AspectJ默认情况下无法织入第三方的库，所以我们定义的切入点无法匹配到，也就导致最终无法采集其点击行为事件。对于这个问题，我们可以新增一个切入点，专门用来匹配ButterKnife@OnClick注解：

```java
 /**
     * 支持 ButterKnife @OnClick 注解
     *
    * @param joinPoint JoinPoint
     */
    @Before("execution(@butterknife.OnClick * *(android.view.View))")
    public void onButterknifeClickAOP(final JoinPoint joinPoint) {
        Log.d(TAG, "点击事件之前：触发埋点操作...");
    }
```

> 为了保证程序的稳定性以及正确性，我们可以修改切入点规则，即我们只匹配带有@OnClick注解，并且仅带有一个View参数的方法，

###### 2、无法采集通过android：OnClick属性绑定的事件

> 对于这个问题，我们可以参考问题1的解决思路，即新增一个注解，然后在android：OnClick属性绑定的方法上用新增的注解标记，最后再新增一个切点匹配这个注解即可。代码略还记得我们AOP自定义注解的切点表达式吗？这里自定义个注解标记即可。代码略。。。。

###### 3、无法采集MenuItem的点击事件

> MenuItem的事件为:
>
> 1、Activity.onOptionsItemSelected(android.view.MenuItem)
>
> 2、Activity.onContextItemSelected(android.view.MenuItem)
>
> 而不是View.Onclick 所以我们单独写个切点方法通过切点表达式匹配这方法即可。

###### 4、setOnClickListener使用了Lambda语法

> 由于目前AspectJ还不支持Lambda语法，所以这个问题暂时无法解决。

### 扩展采集能力

###### 1、支持dialog点击事件

> Dialog的listener是DialogInterface.OnMultiChoiceClickListener类型。如果还要支持这种场景的点击事件，我们就需要针对DialogInterface.OnMultiChoiceClickListener的回调方法onClick(DialogInterface dialogInterface，int which，boolean isChecked)再新增一个切点进行匹配即可。

###### 2、支持采集CheckBox、SwitchCompat、RadioButton、ToggleButton、RadioGroup等点击事件

> 以上控件设置的listener对象均是CompoundButton.OnCheckedChangeListener类型。如果我们要支持采集以上控件的点击事件，同时也需要新增一个对应的切入点用来匹配CompoundButton.OnCheckedChangeListener的回调方法onCheckedChanged(android.widget.CompoundButton，boolean)。新增切点的代码片段

###### 3、其他控件

> RatingBar、SeekBar、Spinner、TabHost、ListView、GridView等控件的事件。
>
> 原理都是一致，找到相应的方法，切点表达式筛选即可。

### 缺点

- 无法织入第三方的库（这个可以参考AspectJX，支持aar，jar包 kotlin切入）
- 由于定义的切点依赖编程语言，目前该方案无法兼容Lambda语法
- 会有一些兼容性方面的问题，比如：D8、Gradle 4.x等

### 小结
> 入门真的简单，等以后见多了、学多了再把这些技术拿来综合练习吧。。。说真的随然aop虽基于修改字节码技术，但是一点也不会字节码相关知识就可以入门aop快乐了，溜溜球下节ASM见。