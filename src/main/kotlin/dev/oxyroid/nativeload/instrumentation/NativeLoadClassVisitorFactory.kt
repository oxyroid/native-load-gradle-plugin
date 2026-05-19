package dev.oxyroid.nativeload.instrumentation

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

interface NativeLoadParameters : InstrumentationParameters {
    @get:Input
    val instrumentedPackages: SetProperty<String>

    @get:Input
    val redirectOwner: Property<String>

    @get:Input
    val redirectMethod: Property<String>
}

abstract class NativeLoadClassVisitorFactory : AsmClassVisitorFactory<NativeLoadParameters> {
    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        return NativeLoadClassVisitor(
            nextClassVisitor = nextClassVisitor,
            redirectOwner = parameters.get().redirectOwner.get(),
            redirectMethod = parameters.get().redirectMethod.get()
        )
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        return parameters.get().instrumentedPackages.get().any { prefix ->
            classData.className.startsWith(prefix)
        }
    }
}

class NativeLoadClassVisitor(
    nextClassVisitor: ClassVisitor,
    private val redirectOwner: String,
    private val redirectMethod: String
) : ClassVisitor(Opcodes.ASM9, nextClassVisitor) {
    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        val visitor = super.visitMethod(access, name, descriptor, signature, exceptions)
        return NativeLoadMethodVisitor(visitor, redirectOwner, redirectMethod)
    }
}
