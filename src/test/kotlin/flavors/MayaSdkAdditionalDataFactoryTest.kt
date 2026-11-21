package flavors

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jdom.Element
import java.nio.file.Files
import java.nio.file.Path

class MayaSdkAdditionalDataFactoryTest : BasePlatformTestCase() {
    fun testAdditionalDataPreservesMayaFlavorAndWorkingDirectory() {
        val directory = Files.createTempDirectory("maya-sdk-test")
        try {
            val data = buildMayaSdkAdditionalData(directory)
            assertSame(INSTANCE, data.flavor)

            val element = Element("additional")
            data.save(element)
            assertEquals(directory.toString(), element.getAttributeValue("WORKING_DIRECTORY"))
            val flavor = element.getChildren("setting").single { it.getAttributeValue("name") == "FLAVOR_ID" }
            assertEquals(INSTANCE.uniqueId, flavor.getAttributeValue("value"))
        } finally {
            Files.deleteIfExists(directory)
        }
    }

    fun testRejectsInvalidWorkingDirectories() {
        val missing = Path.of("missing-maya-sdk-test-directory")
        assertThrows(IllegalArgumentException::class.java) { buildMayaSdkAdditionalData(missing) }

        val file = Files.createTempFile("maya-sdk-test", ".tmp")
        try {
            assertThrows(IllegalArgumentException::class.java) { buildMayaSdkAdditionalData(file) }
            assertThrows(IllegalArgumentException::class.java) { buildMayaSdkAdditionalData(file.resolve("missing")) }
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
