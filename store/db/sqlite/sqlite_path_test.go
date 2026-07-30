package sqlite

import "testing"

func TestDatabaseFilePathStripsDSNQuery(t *testing.T) {
	path, err := DatabaseFilePath("/var/opt/sillage/sillage.db?mode=rwc&cache=shared")
	if err != nil {
		t.Fatalf("DatabaseFilePath() error = %v", err)
	}
	if path != "/var/opt/sillage/sillage.db" {
		t.Fatalf("DatabaseFilePath() = %q", path)
	}
}
