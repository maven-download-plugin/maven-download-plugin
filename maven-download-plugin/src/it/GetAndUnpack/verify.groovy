import junit.framework.Assert;

File f = new File(basedir, "target/ant-contrib-1.0b2-bin.zip");
Assert.assertFalse(f.exists());
f = new File(basedir, "target/ant-contrib/README.txt")
Assert.assertTrue(f.exists());
Assert.assertNotSame(0, f.length());