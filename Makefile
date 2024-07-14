.PHONY: build

clean:
	./gradlew clean

build:
	./gradlew assembleDebug
apk: build

buildRelease:
	./gradlew assembleRelease

install: build
	adb install ./app/build/outputs/apk/**/*.apk

installRelease: buildRelease
	adb install ./app/build/outputs/apk/**/*.apk

run: install open

runRelease: installRelease openRelease

open:
	adb shell am start -n "de.markusressel.kodeeditor.debug/de.markusressel.kodeeditor.demo.ComposeMainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER

openRelease:
	adb shell am start -n "de.markusressel.kodeeditor/de.markusressel.kodeeditor.demo.ComposeMainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
