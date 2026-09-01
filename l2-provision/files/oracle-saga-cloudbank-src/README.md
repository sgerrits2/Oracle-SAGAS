# CloudBank application archive source

`oracle-saga-cloudbank/` is the reviewable source for the LiveLab application archive at `../oracle-saga-cloudbank.zip`.

After changing application configuration, SQL, Compose, or Dockerfiles, rebuild and validate the distributable from the repository root:

```bash
./scripts/build-cloudbank-archive.sh
```

Do not add generated ADB wallet files or a configured `.env` file here. The checked-in `.env` is an unconfigured placeholder; Lab 2 generates the private runtime file in Cloud Shell.
