#! /bin/bash

echo "== Maven version =="
mvn -v

./build-all.sh

./jarsigner-it.sh
