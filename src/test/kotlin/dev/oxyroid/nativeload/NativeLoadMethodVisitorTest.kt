package dev.oxyroid.nativeload

import dev.oxyroid.nativeload.instrumentation.NativeLoadMethodVisitor
import kotlin.test.Test
import kotlin.test.assertEquals
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class NativeLoadMethodVisitorTest {
    @Test
    fun `rewrites System loadLibrary call`() {
        val recorder = RecordingMethodVisitor()
        val visitor = NativeLoadMethodVisitor(recorder, "com/example/NativeLoadRuntime", "loadLibrary")

        visitor.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "java/lang/System",
            "loadLibrary",
            "(Ljava/lang/String;)V",
            false
        )

        assertEquals(
            MethodInvocation(
                Opcodes.INVOKESTATIC,
                "com/example/NativeLoadRuntime",
                "loadLibrary",
                "(Ljava/lang/String;)V",
                false
            ),
            recorder.invocations.single()
        )
    }

    @Test
    fun `does not rewrite unrelated calls`() {
        val recorder = RecordingMethodVisitor()
        val visitor = NativeLoadMethodVisitor(recorder, "com/example/NativeLoadRuntime", "loadLibrary")

        visitor.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "java/lang/System",
            "load",
            "(Ljava/lang/String;)V",
            false
        )

        assertEquals(
            MethodInvocation(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "load",
                "(Ljava/lang/String;)V",
                false
            ),
            recorder.invocations.single()
        )
    }

    private class RecordingMethodVisitor : MethodVisitor(Opcodes.ASM9) {
        val invocations = mutableListOf<MethodInvocation>()

        override fun visitMethodInsn(
            opcode: Int,
            owner: String,
            name: String,
            descriptor: String,
            isInterface: Boolean
        ) {
            invocations.add(MethodInvocation(opcode, owner, name, descriptor, isInterface))
        }
    }

    private data class MethodInvocation(
        val opcode: Int,
        val owner: String,
        val name: String,
        val descriptor: String,
        val isInterface: Boolean
    )
}
