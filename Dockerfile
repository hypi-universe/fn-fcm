FROM hypi/fenrir-runtime-java:v1

ADD target/fn-google-places-*.jar /home/hypi/fenrir/function/fn-google-places.jar
ADD target/lib/* /home/hypi/fenrir/function
