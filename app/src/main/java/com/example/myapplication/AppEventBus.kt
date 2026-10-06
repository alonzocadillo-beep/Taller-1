package com.example.myapplication

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

fun interface DomainEventHandler {
    fun onEvent(event: DomainEvent)
}

/**
 * Bus de eventos en proceso (pub/sub).
 * Publicar es thread-safe; los handlers se invocan en el hilo principal.
 */
object AppEventBus {
    private val handlers = CopyOnWriteArrayList<DomainEventHandler>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun subscribe(handler: DomainEventHandler) {
        if (!handlers.contains(handler)) handlers.add(handler)
    }

    fun unsubscribe(handler: DomainEventHandler) {
        handlers.remove(handler)
    }

    fun publish(event: DomainEvent) {
        val dispatch = {
            handlers.forEach { handler ->
                try {
                    handler.onEvent(event)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dispatch()
        } else {
            mainHandler.post(dispatch)
        }
    }

    fun clear() {
        handlers.clear()
    }
}
