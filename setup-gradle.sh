#!/bin/bash

# Setup Gradle Wrapper for Mirror Project

echo "Setting up Gradle Wrapper for Mirror project..."

# Check if gradle is available
if ! command -v gradle &> /dev/null; then
    echo "Gradle not found. Downloading Gradle wrapper..."
    
    # Create gradle wrapper for mirror-host
    cd mirror-host
    mkdir -p gradle/wrapper
    
    # Download gradle wrapper jar
    curl -L -o gradle/wrapper/gradle-wrapper.jar \
        https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.jar
    
    # Create wrapper properties
    cat > gradle/wrapper/gradle-wrapper.properties << 'WRAPPER'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.2-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
WRAPPER

    # Create gradlew script
    cat > gradlew << 'GRADLEW'
#!/bin/sh
##############################################################################
## Gradle start up script
##############################################################################

# Attempt to set APP_HOME

# Resolve links: $0 may be a link
app_path=$0

# Need this for daisy-chained symlinks.
while
    APP_HOME=${app_path%"${app_path##*/}"}  # leaves a trailing /; empty if no leading path
    [ -h "$app_path" ]
do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in             #(
      /*)   app_path=$link ;; #(
      *)    app_path=$APP_HOME$link ;;
    esac
done

APP_HOME=$( cd "${APP_HOME:-./}" && pwd -P ) || exit

APP_NAME="Gradle"
APP_BASE_NAME=${0##*/}

# Add default JVM options
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD=maximum

warn () {
    echo "$*"
} >&2

die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

# OS specific support
native_path() { printf %s\\n "$1"; }

case "$( uname )" in                #(
  CYGWIN* | MSYS* | MINGW* )  ;;    #(
  *)
    native_path() { printf %s\\n "$1"; }
    ;;
esac

# Collect all arguments for the java command
set -- \
        "-Dorg.gradle.appname=$APP_BASE_NAME" \
        -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
        org.gradle.wrapper.GradleWrapperMain \
        "$@"

# Stop when "xargs" is not available.
if ! command -v xargs >/dev/null 2>&1
then
    die "xargs is not available"
fi

# Use "xargs" to parse quoted args.
eval "set -- $(
        printf '%s\\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" |
        xargs -n1 |
        sed ' s~[^-.:528-9a-zA-Z]~\\&~g; ' |
        tr '\\n' ' '
    )" '"$@"'

exec "$JAVACMD" "$@"
GRADLEW

    chmod +x gradlew
    cd ..
    
    # Copy to mirror-target
    cp -r mirror-host/gradle mirror-target/
    cp mirror-host/gradlew mirror-target/
    
    echo "Gradle wrapper setup complete!"
    echo ""
    echo "To build the apps, run:"
    echo "  cd mirror-host && ./gradlew assembleDebug"
    echo "  cd mirror-target && ./gradlew assembleDebug"
else
    echo "Gradle found: $(gradle --version | head -1)"
    echo ""
    echo "You can build using:"
    echo "  cd mirror-host && gradle assembleDebug"
    echo "  cd mirror-target && gradle assembleDebug"
fi
