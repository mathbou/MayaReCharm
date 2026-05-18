import traceback

from maya import OpenMaya as om

def getPyCharmLogger():
    import os
    import logging
    import logging.handlers

    app_logger_name = os.environ.get("MAYA_DEFAULT_LOGGER_NAME", "")
    log = logging.getLogger(app_logger_name + ".PyCharm")
    log.setLevel(5)
    log.propagate = False

    for h in log.handlers:
        log.removeHandler(h)
    handler = logging.handlers.TimedRotatingFileHandler(r"{0}", when="midnight", encoding="utf8")
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

    if msg_type in [om.MCommandMessage.kInfo, om.MCommandMessage.kResult]:
        log.info(line)
    elif msg_type == om.MCommandMessage.kWarning:
        log.warning(line)
    elif msg_type == om.MCommandMessage.kError:
        log.error(line)
    elif msg_type == om.MCommandMessage.kStackTrace:
        log.exception(line)
    elif msg_type == om.MCommandMessage.kDisplay:
        log.debug(line)
    elif msg_type == om.MCommandMessage.kHistory:
        log.log(5, line.strip())

_pycharm_logger = getPyCharmLogger()

try:
    om.MCommandMessage.removeCallback(_pycharm_logger_callback_id)
except NameError as e:
    pass
finally:
    _pycharm_logger_callback_id = om.MCommandMessage.addCommandOutputCallback(writeToTerminal, _pycharm_logger)
    _pycharm_logger.info("PyCharm logger initialized and callback registered.")