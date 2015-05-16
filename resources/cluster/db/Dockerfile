# Set up datomic-free as a docker

# This is based pretty heavily on tauho/docker-datomic-free
# Which is Copyright 2013, Cognitect Inc, All Rights Reserved.
# But they put it on github, with invitations to fork,
# make changes, and submit pull requests.
# They also included a copy of the Datomic Free License.

# I can't imagine they'd object to this particular use
# (why else would they have supplied what they did?)

# 2015MAY16:
# I've contacted timgluz@gmail.com to verify that.

FROM dockerfile/java

RUN apt-get update && apt-get -y \
    curl \
    unzip

RUN groupadd -r datomic && useradd -r -g datomic datomic
USER datomic

RUN mkdir ~/datomic

ENV DATOMIC_VERSION 0.9.5173

RUN curl --user-agent 'frereth (builds@frereth.com' \
    --location \
    --url "https://my.datomic.com/downloads/free/${DATOMIC_VERSION}" \
    --output ~/datomic.zip

RUN unzip -u ~/datomic.zip -d ~
RUN mv ~/datomic-free-${DATOMIC_VERSION} ~/datomic/
RUN rm ~/datomic.zip

RUN cp ~/datomic/config/samples/free-transactor-template.properties ~/datomic/free-transactor.properties

RUN sed "s/host=localhost/host=0.0.0.0" -i ~/datomic/free-transactor.properties

ENTRYPONT ["/home/datomic/datomic/bin/transactor"]
CMD ["/home/datomic/datomic/free-transactor.properties" ]

EXPOSE 4334 4335 4336
