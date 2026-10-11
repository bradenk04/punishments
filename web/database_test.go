package main

import (
	"context"
	"database/sql"
	"os"
	"testing"
)

func TestRealSharedDatabases(t *testing.T) {
	for _, item := range []struct{ key, driver string }{{"MYSQL_DSN", "mysql"}, {"POSTGRES_DSN", "pgx"}} {
		t.Run(item.driver, func(t *testing.T) {
			dsn := os.Getenv(item.key)
			if dsn == "" {
				t.Skip("Set " + item.key + " to run integration tests")
			}
			db, err := sql.Open(item.driver, dsn)
			if err != nil {
				t.Fatal(err)
			}
			defer db.Close()
			store := SQLStore{db, item.driver}
			entries, _, err := store.List(context.Background(), Filter{Search: "IntegrationPlayer"}, []string{"BAN"})
			if err != nil {
				t.Fatal(err)
			}
			if len(entries) != 1 || entries[0].ID != "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb" || entries[0].IssuedAt != 1700000000000 {
				t.Fatal(entries)
			}
			entries, _, err = store.List(context.Background(), Filter{Player: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"}, []string{"BAN"})
			if err != nil || len(entries) != 1 {
				t.Fatal(entries, err)
			}
			entries, _, err = store.List(context.Background(), Filter{Search: "%"}, []string{"BAN"})
			if err != nil || len(entries) != 0 {
				t.Fatal("Wildcard was not escaped", entries, err)
			}
			entries, _, err = store.List(context.Background(), Filter{ID: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"}, []string{"MUTE"})
			if err != nil || len(entries) != 0 {
				t.Fatal("Private type returned", entries, err)
			}
		})
	}
}
