FROM ubuntu:bionic AS builder1
ARG THRIFT_VERSION=0.9.3
ENV DEBIAN_FRONTEND noninteractive
RUN apt-get update && \
    apt-get install -y \
      zip \
      curl \
      build-essential \
      libtool \
      pkg-config \
      automake \
      bison \
      flex \
      ;
WORKDIR /src
ADD https://github.com/apache/thrift/archive/refs/tags/${THRIFT_VERSION}.zip /src/
RUN unzip ${THRIFT_VERSION}.zip
RUN cd thrift-${THRIFT_VERSION} && ./bootstrap.sh && \
    ./configure --disable-debug --disable-tests --disable-libs && \
    make && \
    make install

FROM maven:3.9.6-amazoncorretto-8 AS builder2
COPY . /code
WORKDIR /code
RUN mvn package


FROM maven:3.9.6-amazoncorretto-8
COPY --from=builder1 /usr/local/bin/thrift /usr/local/bin/thrift
COPY --from=builder2 /code/target/*.jar /workspace/
COPY --from=builder2 /code/gen.sh /workspace/
COPY --from=builder2 /code/template.xml /workspace/
WORKDIR /workspace
RUN mkdir -p app && mkdir -p services
VOLUME [ "/app" ]
CMD [ "java", "-cp", "HttpToThrift-0.0.1-SNAPSHOT.jar:.", "com.xiaogenban1993.http2thrift.Application"]