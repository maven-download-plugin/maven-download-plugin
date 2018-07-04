import junit.framework.Assert;

File target = new File(basedir, "target/");
if (!target.exists()) {
  target.mkdirs();
  Assert.assertTrue("Folder " + target.absolutePath + " must exist", target.exists());
}

File f = new File(basedir, "target/lolcatsdotcomcm90ebvhwphtzqvf.jpg");
if (f.exists()) {
  f.delete();
}
f.createNewFile();

Assert.assertTrue("File " + f.absolutePath + " must exist", f.exists());
Assert.assertNotSame("File need to be empty", 0, f.length());
