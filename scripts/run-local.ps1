param(
    [switch]$SkipDocker
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

if (-not $SkipDocker) {
    docker compose up -d
}

$env:DB_URL = "jdbc:mysql://localhost:3306/seckill?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "change-me"
$env:REDIS_HOST = "localhost"
$env:REDIS_PORT = "6379"
$env:ROCKETMQ_NAME_SERVER = "127.0.0.1:9876"
$env:ELASTICSEARCH_URIS = "http://localhost:9200"

Write-Host "Starting Seckill backend..."
& "$repo\mvnw.cmd" spring-boot:run
