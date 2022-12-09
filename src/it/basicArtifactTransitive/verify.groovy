def verifyFile(fileName) {
    def file = new File(basedir, "target/${fileName}")
    assert file.exists() : "File $file.absolutePath does not exist"
    assert file.size() != 0
}

verifyFile('dummy-api-1.0.jar')
verifyFile('dummy-impl-1.0.jar')

// only up to 1-level dependencies should be downloaded
assert !( new File(basedir, 'target/dummy-dependency-1.0.jar').exists() )
