if [ -z "$JAVA_HOME" ]; then
    echo "**** Please set JAVA_HOME correctly."
    exit 1
fi

# Switch to folder containing application.
cd ../images/image-*/DemoBench.app

INSTALL_HOME=Contents/PlugIns/Java.runtime/Contents/Home/jre/bin
mkdir -p $INSTALL_HOME
cp $JAVA_HOME/jre/bin/java $INSTALL_HOME