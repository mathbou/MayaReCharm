package resources

import java.text.MessageFormat

enum class PythonStrings(val message: String) {
    UTF8_ENCODING_STR("# -*- coding: utf-8 -*-"),
    OPEN_LOG(PythonStrings::class.java.classLoader.getResource("python/open_log.py")?.readText()?: ""),

    // no clue why the maya.cmds part is needed, but it works and prevents things from getting executing twice
    EXECFILE("python(\"exec(compile(open(\\\"{0}\\\", encoding=\\\"utf-8\\\").read(), \\\"{0}\\\", \\\"exec\\\"))\")"),
    STOPTRACE("import pydevd; pydevd.stoptrace()"),
    CMDPORTSETUPSCRIPT("python/command_port_setup.py");

    fun format(vararg args: Any): String = MessageFormat.format(message, *args)

    fun getResource(vararg args: Any): String {
        val text = this::class.java.classLoader.getResource(message)?.readText() ?: return ""
        return MessageFormat.format(text, *args)
    }
}
