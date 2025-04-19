import traceback

from maya import OpenMaya as om


def getPyCharmLogger():
    import os
    import logging

    appLoggerName = os.environ.get("MAYA_DEFAULT_LOGGER_NAME", "")
    log = logging.getLogger(appLoggerName + ".PyCharm")
    log.setLevel(logging.DEBUG)
    log.propagate = False

    log.removeHandler(h for h in log.handlers)
    handler = logging.FileHandler(r"{0}", mode="a", encoding="utf8")
    log.addHandler(handler)

    formatter = logging.Formatter("%(asctime)s - %(levelname)s - %(message)s")
    handler.setFormatter(formatter)

    return log


def writeToTerminal(msg, msg_type, log):
    """This is the callback function that gets called when Maya wants to print something.

    It will outputs the msg to the Pycharm logger.
    """
    line = str(msg).replace("\0\u000A", "")
    if not line.strip():
        return

    if msg_type == om.MCommandMessage.kWarning:
        log.warning(line)
    elif msg_type == om.MCommandMessage.kError:
        log.error(line)
    elif msg_type in [om.MCommandMessage.kResult, om.MCommandMessage.kInfo, om.MCommandMessage.kDisplay]:
        log.info(line)
    elif msg_type == om.MCommandMessage.kStackTrace:
        log.exception(line)


_pycharm_logger = getPyCharmLogger()

try:
    om.MCommandMessage.removeCallback(_pycharm_logger_callback_id)
except NameError as e:
    traceback.print_exception(e)
finally:
    _pycharm_logger_callback_id = om.MCommandMessage.addCommandOutputCallback(writeToTerminal, _pycharm_logger)