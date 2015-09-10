import junit.framework.Assert;

File f = new File(basedir, "target/lolcatsdotcomcm90ebvhwphtzqvf.jpg");
Assert.assertTrue("File " + f.absolutePath + " does not exist", f.exists());
Assert.assertNotSame("File is empty", 0, f.length());