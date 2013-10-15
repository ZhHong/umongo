#!/bin/sh

pkgdir=`dirname $0`
echo Moving to package folder $pkgdir
cd $pkgdir
version=`cat ../manifest.mf | grep Bundle-Version | cut -d " " -f2`
vdash=`echo $version | sed -e s/'\.'/-/g`

cp ../dist/lib/*.jar ../lib/

function package_build {
	os=$1
	arch=$2
	appdir=umongo-${os}-${arch}_${vdash}
	echo Creating folder $appdir
	rm -rf $appdir ${appdir}.zip
	mkdir $appdir

	cp ./common-files/* $appdir/
	cp ${os}/* $appdir/
	cp ../dist/umongo.jar $appdir/
	mkdir $appdir/lib
	cp ../lib/*.jar $appdir/lib/
    cp ../README $appdir/
	mkdir $appdir/plugins

	zip -r ${appdir}.zip $appdir
}

package_build windows all
package_build linux all

./osx/package.sh
