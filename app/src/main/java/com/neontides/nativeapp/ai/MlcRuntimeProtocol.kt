package com.neontides.nativeapp.ai

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

/** IPC minimale, senza AIDL, tra la UI e il processo MLC protetto. */
internal object MlcRuntimeProtocol {
    const val DESCRIPTOR = "com.neontides.nativeapp.ai.MlcRuntimeService"
    const val LOAD = IBinder.FIRST_CALL_TRANSACTION
    const val IS_LOADED = IBinder.FIRST_CALL_TRANSACTION + 1
    const val GENERATE = IBinder.FIRST_CALL_TRANSACTION + 2
    const val UNLOAD = IBinder.FIRST_CALL_TRANSACTION + 3
    const val LAST_ERROR = IBinder.FIRST_CALL_TRANSACTION + 4
}

internal class MlcRuntimeClient(private val binder: IBinder) : IInterface {
    override fun asBinder(): IBinder = binder

    fun loadModel(modelPath: String, modelLib: String): Boolean = transact { data, reply ->
        data.writeString(modelPath)
        data.writeString(modelLib)
        binder.transact(MlcRuntimeProtocol.LOAD, data, reply, 0)
        reply.readException()
        reply.readInt() != 0
    }

    fun isModelLoaded(modelPath: String): Boolean = transact { data, reply ->
        data.writeString(modelPath)
        binder.transact(MlcRuntimeProtocol.IS_LOADED, data, reply, 0)
        reply.readException()
        reply.readInt() != 0
    }

    fun generate(context: String, prompt: String, maxTokens: Int, temperature: Float): String =
        transact { data, reply ->
            data.writeString(context)
            data.writeString(prompt)
            data.writeInt(maxTokens)
            data.writeFloat(temperature)
            binder.transact(MlcRuntimeProtocol.GENERATE, data, reply, 0)
            reply.readException()
            reply.readString().orEmpty()
        }

    fun unloadModel(): Boolean = transact { data, reply ->
        binder.transact(MlcRuntimeProtocol.UNLOAD, data, reply, 0)
        reply.readException()
        reply.readInt() != 0
    }

    fun lastError(): String = transact { data, reply ->
        binder.transact(MlcRuntimeProtocol.LAST_ERROR, data, reply, 0)
        reply.readException()
        reply.readString().orEmpty()
    }

    private inline fun <T> transact(block: (Parcel, Parcel) -> T): T {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(MlcRuntimeProtocol.DESCRIPTOR)
            block(data, reply)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}

internal abstract class MlcRuntimeBinder : Binder() {
    init {
        attachInterface(null, MlcRuntimeProtocol.DESCRIPTOR)
    }

    abstract fun loadModel(modelPath: String, modelLib: String): Boolean
    abstract fun isModelLoaded(modelPath: String): Boolean
    abstract fun generate(context: String, prompt: String, maxTokens: Int, temperature: Float): String
    abstract fun unloadModel(): Boolean
    abstract fun lastError(): String

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == INTERFACE_TRANSACTION) {
            reply?.writeString(MlcRuntimeProtocol.DESCRIPTOR)
            return true
        }
        val target = requireNotNull(reply) { "Risposta Binder assente" }
        data.enforceInterface(MlcRuntimeProtocol.DESCRIPTOR)
        return try {
            when (code) {
                MlcRuntimeProtocol.LOAD -> {
                    val result = loadModel(data.readString().orEmpty(), data.readString().orEmpty())
                    target.writeNoException()
                    target.writeInt(if (result) 1 else 0)
                }
                MlcRuntimeProtocol.IS_LOADED -> {
                    val result = isModelLoaded(data.readString().orEmpty())
                    target.writeNoException()
                    target.writeInt(if (result) 1 else 0)
                }
                MlcRuntimeProtocol.GENERATE -> {
                    val result = generate(
                        data.readString().orEmpty(),
                        data.readString().orEmpty(),
                        data.readInt(),
                        data.readFloat()
                    )
                    target.writeNoException()
                    target.writeString(result)
                }
                MlcRuntimeProtocol.UNLOAD -> {
                    val result = unloadModel()
                    target.writeNoException()
                    target.writeInt(if (result) 1 else 0)
                }
                MlcRuntimeProtocol.LAST_ERROR -> {
                    target.writeNoException()
                    target.writeString(lastError())
                }
                else -> return super.onTransact(code, data, target, flags)
            }
            true
        } catch (t: Throwable) {
            target.writeException(IllegalStateException(t.message ?: t.javaClass.simpleName))
            true
        }
    }
}
