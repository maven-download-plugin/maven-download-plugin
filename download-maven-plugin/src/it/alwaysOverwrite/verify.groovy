import junit.framework.Assert;

/**
 * Scenario:
 * Given plugin configuration:
 *  - skipCache = true
 *  - overwrite = true
 *  - no signature specified
 * When Run the plugin to download the zip
 * And Save timestamp into "stamp" file
 * And Run the plugin to download the same zip
 * Then the file should be overwritten
 *
 * `jpg_file_name` is injected through scriptVariables
 */
File target = new File(basedir, "target");
File zip = new File(target, jpg_file_name);
Assert.assertTrue(zip.exists());
File stamp = new File(target, "stamp");
Assert.assertTrue(
  "Timestamp file should exist",
  stamp.exists()
);
Assert.assertTrue(
  "File expected to be modified but it wasn't",
  stamp.text != String.valueOf(zip.lastModified())
);