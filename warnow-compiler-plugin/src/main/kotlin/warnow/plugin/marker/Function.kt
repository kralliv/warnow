package warnow.plugin.marker

import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor

interface WarnowSyntheticMember {

    val kind: Kind
}

interface WarnowSyntheticFunction : WarnowSyntheticMember, FunctionDescriptor
interface WarnowSyntheticProperty : WarnowSyntheticMember, PropertyDescriptor

enum class Kind {

    Unknown,

    DefineFunction,
    ExpectFunction,
    AccessFunction,
    MutateFunction,

    ValueAccess,
    PackageAccess,
    PackageAccessWithContext,
    PackageAccessWithBlockAndContext
}
