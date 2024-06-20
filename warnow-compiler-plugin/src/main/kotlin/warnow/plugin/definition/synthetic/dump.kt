package warnow.plugin.definition.synthetic

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import warnow.plugin.log.Logging

fun dumpCallableReceivers(callable: CallableDescriptor) {
    return

    LOG.debug { "receivers of $callable" }

    callable.dispatchReceiverParameter?.let { dispatchReceiver ->
        LOG.debug { " dispatcher" }
        LOG.debug { "  " + dispatchReceiver::class.java.name }
        LOG.debug { "  $dispatchReceiver" }
    }

    callable.extensionReceiverParameter?.let { extensionReceiver ->
        LOG.debug { " extension" }
        LOG.debug { "  " + extensionReceiver::class.java.name }
        LOG.debug { "  $extensionReceiver" }

        LOG.debug { "  " + extensionReceiver.value::class.java.name }
        LOG.debug { "  " + extensionReceiver.value }
    }
}

private val LOG = Logging.logger { }