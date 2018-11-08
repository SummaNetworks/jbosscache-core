#!/bin/bash

###################################################################################################

#  CONFIGURABLE VARIABLES

# Base SVN directory for this release.  There should be a "tags" and "trunk" directory under this.
svnBase="https://svn.jboss.org/repos/jbosscache/core"

# Name of the "tags" directory
svnTags="tags"

# Where do you locally check out tags?
localTagsDir="/Users/manik/Code/jbosscache/core/tags"

# Your maven2 repo to deploy built artifacts
localMvnRepoDir="/Users/manik/Code/maven2/org/jboss/cache/jbosscache-core"

# Where should built documentation go?
docsDir="/Users/manik/Code/CMS/jbosscache/freezone/docs"

###################################################################################################

# Functions

help()
{
	echo 'Usage: '
	echo '    $ release.sh <version>'
	echo '  where version is represented by major.minor.micro.modifier'
	echo '  e.g.:'
	echo '    $ release.sh 3.0.1.BETA1'
	echo '  version modifier needs to be uppercase, and end with a numeric, except if it is GA.'
	echo
}

validate()
{
	if [ ! $ver ]
	then
		echo "Missing version number!"
		help
		exit 0
	fi
	
	if [[ $ver =~ ^[1-9]\.[0-9]\.[0-9]\.(GA|(ALPHA|BETA|CR|SP)[1-9][0-9]?)$ ]]
	then
		#matches!
		echo "Releasing version $ver"
	else
		echo "Incorrect version format for version $ver!"
		help
		exit 0
	fi
}

tag()
{
	svn cp ${svnBase}/trunk ${svnBase}/${svnTags}/$ver -m "JBoss Cache Release Script: Tagging $ver"
	cd $localTagsDir
	svn co ${svnBase}/${svnTags}/$ver
	cd $ver	
}

setVersion()
{
	# Change pom.xml and Version.java
	sed -e "s/<jbosscache-core-version>[1-9]\.[0-9]\.[0-9]-SNAPSHOT/<jbosscache-core-version>$ver/g" pom.xml > newpom.xml
	mv newpom.xml pom.xml
	verBytes=`echo $ver | sed -e "s/\([0-9A-Z]\)/'\1',/g" -e "s/,$/};/" -e "s/^/{'0',/" -e "s/\.//g"`
	sed -e "s/\"[1-9]\.[0-9]\.[0-9]-SNAPSHOT\"/\"$ver\"/g" -e "s/version_id = {[0-9A-Z', ]*};/version_id = $verBytes/g" src/main/java/org/jboss/cache/Version.java > tmp.java
	mv tmp.java src/main/java/org/jboss/cache/Version.java
 	svn ci -m "JBoss Cache Release Script: Updating $ver" pom.xml src/main/java/org/jboss/cache/Version.java
}

build()
{
	mvn clean deploy -Dmaven.test.skip.exec=true -PDocs
}

checkIntoRepository()
{
	cd $localMvnRepoDir
	svn add $ver
	svn ci -m "JBoss Cache Release Script: Releasing $ver" $ver
}

docs()
{
	cd $docsDir
	mkdir tmpDir
	cd tmpDir
	unzip -a $localMvnRepoDir/$ver/jbosscache-core-$ver-doc.zip
	mv jbosscache-core-$ver/doc ../$ver
	cd ..
	rm -rf tmpDir
	svn add $ver
	svn ci -m "JBoss Cache Release Script: Docs for version $ver" $ver	
}


### The actual script

ver=${1}
echo "JBoss Cache Release Script"
validate
tag
setVersion
build
checkIntoRepository
docs
echo 'Done!  Now all you need to do is:'
echo '  1.  Update the website (http://www.jbosscache.org)'
echo '  2.  Update wiki pages (main wiki page, docs and download)'
echo '  3.  Announce and blog about this!'
echo


