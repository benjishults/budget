# Set up to run

By default, this stores its data in files in a folder named `~/.data/bps-budget`. If you want to store
the data elsewhere, you'll need a `budget.yml` file in your `~/.config/bps-budget` folder.

It should contain the following:

```yaml
persistence:
    type: FILE
    file:
        dataDirectory: /path/to/where/you/want/the/data
```

Once data are created in the data folder, they shouldn't be tampered with.
