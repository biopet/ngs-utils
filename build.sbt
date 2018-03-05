organization := "com.github.biopet"
organizationName := "Biopet"
name := "ngs-utils"

biopetUrlName := "ngs-utils"

startYear := Some(2014)

biopetIsTool := false

developers += Developer(id = "ffinfo",
                        name = "Peter van 't Hof",
                        email = "pjrvanthof@gmail.com",
                        url = url("https://github.com/ffinfo"))

scalaVersion := "2.11.12"

libraryDependencies += "com.github.biopet" %% "common-utils" % "0.3"
libraryDependencies += "com.github.samtools" % "htsjdk" % "2.14.3"

libraryDependencies += "com.github.biopet" %% "test-utils" % "0.3" % Test
