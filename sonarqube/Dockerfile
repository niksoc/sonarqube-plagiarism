FROM openjdk:11

# Http port
EXPOSE 9000

COPY . /app

RUN /app/build.sh

ENTRYPOINT /app/run.sh
