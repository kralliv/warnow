package warnow.plugin.generation

import warnow.plugin.resolution.CallResolutionContainer

class CommandLineExpressionCodegenExtension(callResolutionContainer: CallResolutionContainer, optimizeBytecode: Boolean) :
    WarnowExpressionCodegenExtension(callResolutionContainer, optimizeBytecode)