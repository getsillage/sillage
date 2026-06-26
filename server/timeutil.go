package server

import "time"

func unixMilliTime(value int64) time.Time {
	return time.UnixMilli(value).UTC()
}
