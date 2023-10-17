set -x -e

mvn package
mvn org.gephi:gephi-maven-plugin:run