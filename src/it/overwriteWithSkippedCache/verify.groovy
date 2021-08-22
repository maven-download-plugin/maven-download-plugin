/**
 * Covers the bug https://github.com/maven-download-plugin/maven-download-plugin/issues/194
 *
 * Scenario:
 * Given plugin configuration:
 *  - skipCache = false (default)
 *  - overwrite = true
 *  - no checksum specified
 * When Run the plugin to download the zip
 * And Save timestamp into "stamp" file
 * And Run the plugin to download the same zip
 * Then the file should be overwritten
 *
 * `img_file_name` is injected through scriptVariables
 */
File target = new File(basedir, "target")
File zip = new File(target, img_file_name)
assert zip.exists()
File stamp = new File(target, "stamp")
assert stamp.exists() : "Timestamp file should exist"
assert stamp.text != String.valueOf(zip.lastModified()) : "File expected to be modified but it wasn't"
