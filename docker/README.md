== Build

    docker build -t eclipse-signing-service .

== Run

    docker run -d -p 31338:80 eclipse-signing-service

=== Mounting your own signing-service code

If you are developing new eclipse-signing-service code and want to test
it, simply mount a volume for your code to /var/www/signing-service for
example:

    -v /path/to/code:/var/www/signing-service

=== Mounting your own Signing Certificates

If you would like to mount your own signing certificates you can
mount your own data volume to /etc/ssl/servercerts

    -v /path/to/certs:/etc/ssl/servercerts

You will need to place 2 files in this directory "keystore" which
contains your Java Keystore and "keystore.passwd" which is a single
line file containing the password to your keystore file.

== Maven build with custom signing service

    mvn clean verify -Dcbi.jarsigner.signerUrl=http://localhost:31338/jarsign.php

Example with CBI Maven plugins:

    git clone https://git.eclipse.org/r/cbi/signing-service
    cd signing-service
    mvn clean verify -Dcbi.jarsigner.signerUrl=http://localhost:31338/jarsign.php -Prelease

