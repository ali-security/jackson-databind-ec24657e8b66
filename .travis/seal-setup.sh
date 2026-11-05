# Sourced from .travis.yml before_install: provisions the leg's JDK and Maven 3.8.8.
if [ -n "$SEAL_JDK" ]; then
  BASE_CACERTS="$JAVA_HOME/jre/lib/security/cacerts"
  [ -f "$BASE_CACERTS" ] || BASE_CACERTS="$JAVA_HOME/lib/security/cacerts"
  mkdir -p /tmp/seal-jdk
  curl -fsSL --retry 5 --retry-delay 10 "https://cdn.azul.com/zulu/bin/${SEAL_JDK}.tar.gz" | tar xz --strip-components=1 -C /tmp/seal-jdk || return 1
  # Zulu 7's bundled truststore predates today's Maven Central chain.
  cp "$BASE_CACERTS" /tmp/seal-jdk/jre/lib/security/cacerts || return 1
  export JAVA_HOME=/tmp/seal-jdk
  export PATH="$JAVA_HOME/bin:$PATH"
  # JDK 7 does not offer TLS 1.2 to servers by default.
  export MAVEN_OPTS="-Dhttps.protocols=TLSv1.2 ${MAVEN_OPTS:-}"
fi
mkdir -p /tmp/seal-maven
# Maven Central first, Apache archive as fallback: a single connection reset must not fail the leg.
# (focal's curl 7.68 predates --retry-all-errors, hence the explicit loop.)
for MVN_URL in https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.8.8/apache-maven-3.8.8-bin.tar.gz \
               https://archive.apache.org/dist/maven/maven-3/3.8.8/binaries/apache-maven-3.8.8-bin.tar.gz \
               https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.8.8/apache-maven-3.8.8-bin.tar.gz; do
  curl -fsSL --retry 5 --retry-delay 10 -o /tmp/seal-maven.tgz "$MVN_URL" && break
  sleep 10
done
tar xzf /tmp/seal-maven.tgz --strip-components=1 -C /tmp/seal-maven || return 1
export M2_HOME=/tmp/seal-maven
export PATH="/tmp/seal-maven/bin:$PATH"
java -version
mvn -version
