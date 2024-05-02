FROM gradle:8.3.0-jdk17
WORKDIR /app
COPY . .
RUN gradle clean compileKotlin -PprodBuild=true
CMD gradle run -PprodBuild=true
EXPOSE 27888-27999:27888-27999/udp
