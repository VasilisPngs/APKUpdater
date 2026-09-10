$ErrorActionPreference = 'Stop'

# Generates a new release keystore and encrypts it for storage in Git.
# The password is generated locally and is never printed or sent anywhere.

$root = Split-Path -Parent $PSScriptRoot
$keystore = Join-Path $root 'release.keystore'
$encrypted = Join-Path $root 'release.keystore.enc'

if (Test-Path $keystore) { throw "release.keystore already exists." }
if (Test-Path $encrypted) { throw "release.keystore.enc already exists." }

$password = -join ((1..32) | ForEach-Object { [char]((48..57) + (65..90) + (97..122) | Get-Random) })

Write-Host 'Generating release keystore...'
& keytool -genkeypair -v `
  -keystore $keystore `
  -alias release `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000 `
  -storepass $password `
  -keypass $password `
  -dname 'CN=APKUpdater, O=VasilisPngs, C=GR'

if ($LASTEXITCODE -ne 0) { throw 'keytool failed.' }

$secure = ConvertTo-SecureString $password -AsPlainText -Force
$encryptedPassword = Join-Path $root 'release-keystore-password.txt'
Set-Content -Path $encryptedPassword -Value $password -NoNewline

Write-Host 'Encrypting keystore with OpenSSL...'
& openssl enc -aes-256-cbc -pbkdf2 -salt -in $keystore -out $encrypted -pass "pass:$password"
if ($LASTEXITCODE -ne 0) { throw 'openssl failed.' }

Remove-Item $keystore -Force
Write-Host ''
Write-Host 'Created:'
Write-Host "  $encrypted"
Write-Host "  $encryptedPassword"
Write-Host ''
Write-Host 'Set these GitHub Actions secrets:'
Write-Host '  ANDROID_KEYSTORE_PASSWORD = contents of release-keystore-password.txt'
Write-Host '  ANDROID_KEY_ALIAS = release'
Write-Host ''
Write-Host 'After adding the secret, securely delete release-keystore-password.txt.'
