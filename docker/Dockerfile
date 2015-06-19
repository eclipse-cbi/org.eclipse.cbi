FROM debian:7

RUN apt-get update && apt-get install -y apache2 \
                                         libapache2-mod-php5 \
                                         git \
                                         openjdk-7-jdk

RUN sed -i -e "s/^post_max_size.*/post_max_size = 1G/" \
           -e "s/^upload_max_filesize.*/upload_max_filesize = 1G/" \
           /etc/php5/apache2/php.ini

RUN sed -i -e "s/\/var\/www/\/var\/www\/signing-service/" \
           /etc/apache2/sites-enabled/000-default

RUN mkdir /etc/ssl/servercerts && \
    echo "fortesting" > /etc/ssl/servercerts/keystore.passwd && \
    keytool -genkey -keyalg RSA \
            -keystore /etc/ssl/servercerts/keystore \
            -storepass fortesting \
            -keypass fortesting \
            -alias example.org \
            -dname "CN=localhost, O=Example.org"

RUN git clone https://git.eclipse.org/r/cbi/signing-service /var/www/signing-service && \
    cp /var/www/signing-service/localsettings.php.sample /var/www/signing-service/localsettings.php
WORKDIR /var/www/signing-service

EXPOSE 80
CMD ["apachectl", "-DFOREGROUND"]
