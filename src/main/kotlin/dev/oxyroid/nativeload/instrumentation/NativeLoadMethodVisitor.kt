package dev.oxyroid.nativeload.instrumentation

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class NativeLoadMethodVisitor(
    methodVisitor: MethodVisitor,
    private val redirectOwner: String,
    private val redirectMethod: String
) : MethodVisitor(Opcodes.ASM9, methodVisitor) {
    override fun visitMethodInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean
    ) {
        if (
            opcode == Opcodes.INVOKESTATIC &&
            owner == "java/lang/System" &&
            name == "loadLibrary" &&
            descriptor == "(Ljava/lang/String;)V"
        ) {
            super.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                redirectOwner,
                redirectMethod,
                "(Ljava/lang/String;)V",
                false
            )
            return
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }
}
