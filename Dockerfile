FROM maven:3.9.6-amazoncorretto-8
COPY . /code
WORKDIR /code
RUN mvn package

