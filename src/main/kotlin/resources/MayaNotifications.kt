package resources

import MayaBundle as Loc
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

private const val displayGroup = "MayaReCharm"
private const val titleText = "MayaReCharm"

object MayaNotifications {
    val CONNECTION_REFUSED = Notification(
        displayGroup, titleText,
        Loc.message("mayarecharm.notifications.ConnectionRefused"), NotificationType.ERROR
    )

    val FILE_FAIL = Notification(
        displayGroup, titleText,
        Loc.message("mayarecharm.notifications.FailedToCreateTempFile"), NotificationType.ERROR
    )

    val NO_SDK_SELECTED = Notification(
        displayGroup, titleText,
        Loc.message("mayarecharm.notifications.NoSdkSelected"), NotificationType.ERROR
    )

    val INVALID_SDK_SELECTED = Notification(
        displayGroup, titleText,
        Loc.message("mayarecharm.notifications.InvalidSdkSelected"), NotificationType.ERROR
    )

    fun mayaInstanceNotFound(instancePath: String, project: Project) {
        Notification(
            displayGroup,
            titleText,
            Loc.message("mayarecharm.notifications.NoRunningMayaInstanceFor", instancePath),
            NotificationType.ERROR
        ).notify(project)
    }
}
