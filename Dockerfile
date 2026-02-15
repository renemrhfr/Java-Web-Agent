FROM mcr.microsoft.com/playwright/java:v1.58.0-noble
WORKDIR /app
COPY target/Browser.jar app.jar
# Create a new User, so this doesnt act as root
RUN mkdir -p /app/data \
 && if ! getent group pwuser >/dev/null; then addgroup --system pwuser; fi \
 && if ! id -u pwuser >/dev/null 2>&1; \
    then adduser --system --ingroup pwuser --home /nonexistent --shell /usr/sbin/nologin pwuser; fi \
 && chown -R pwuser:pwuser /app
USER pwuser
EXPOSE 8080
# Performance Improvements. Play around with those depending on your hardware.
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=60", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=100", \
  "-XX:+ParallelRefProcEnabled", \
  "-Djava.security.egd=file:/dev/urandom", \
  "-jar", "/app/app.jar"]
