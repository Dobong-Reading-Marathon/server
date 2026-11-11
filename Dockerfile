# 운영 서버 이미지 (Cloud Run). 배포 방법은 레포 밖 배포 문서를 따릅니다.

# 1단계: 실행용 jar 빌드 (테스트는 CI에서 돌리므로 여기서는 건너뜀)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
# 의존성부터 받아 두면, 소스만 바뀌었을 때 이 단계를 다시 쓰지 않습니다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon > /dev/null
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# 2단계: 실행
FROM eclipse-temurin:17-jre
# 서버 시간대를 한국으로. UTC면 한국 시간 0~9시에 오늘 날짜 독서기록이 미래로 판단돼 400이 납니다.
ENV TZ=Asia/Seoul
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
