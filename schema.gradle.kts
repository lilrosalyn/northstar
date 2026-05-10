import cc.ekblad.toml.decode
import cc.ekblad.toml.tomlMapper
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

buildscript {
    repositories {
        mavenCentral()
        maven("https://jitpack.io/")
    }

    dependencies {
        classpath("com.squareup:kotlinpoet:2.2.0")
        classpath("cc.ekblad:4koma:1.2.0")
    }
}

tasks.register("generateTomlSchemas") {
    doLast {
        val mapper = tomlMapper {}
        val resource = file("src/main/resources/lang/en.toml")

        if (!resource.exists())
            return@doLast

        val document = mapper.decode<Map<String, Any>>(resource.toPath())

        fun String.propercase()
                = this[0].uppercase() + substring(1)

        fun compileMap(name: String, map: Map<String, Any>): TypeSpec {
            val builder = TypeSpec.classBuilder(name)
                .addModifiers(KModifier.DATA)
                .addSuperinterface(ClassName(
                    "dev.rosalyn.northstar.language",
                    "Language"
                ))

            val constructor = FunSpec.constructorBuilder()

            for ((key, value) in map) {
                when (value) {
                    is Map<*, *> -> {
                        val className = ClassName("", key.propercase())
                        @Suppress("UNCHECKED_CAST")
                        val type = compileMap(className.simpleName, value as Map<String, Any>)
                        constructor.addParameter(key, className)
                        builder.addType(type)
                        builder.addProperty(PropertySpec.builder(key, className)
                            .initializer(key)
                            .build())
                    }
                    else -> {
                        constructor.addParameter(key, value::class)
                        builder.addProperty(PropertySpec.builder(key, value::class)
                            .initializer(key)
                            .build())
                    }
                }
            }

            return builder.primaryConstructor(constructor.build())
                .build()
        }

        val file = FileSpec.builder("dev.rosalyn.northstar", "Language")
            .addType(compileMap("CoreLanguage", document))
            .build()

        val outFile = file("build/generated/main/kotlin/")
        file.writeTo(outFile)
    }
}