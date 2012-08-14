package nu.glen.followbackbot

import collection.JavaConverters._
import com.twitter.conversions.time._
import com.twitter.util.Duration
import com.google.common.cache.CacheBuilder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import twitter4j.User

object RateLimiter {
  val defaultMaxSize = 10000

  def perDay(maxValue: Int, maxSize: Int = defaultMaxSize): RateLimiter =
    new SimpleRateLimiter(24.hours, maxValue, maxSize)

  def perHour(maxValue: Int, maxSize: Int = defaultMaxSize): RateLimiter =
    new SimpleRateLimiter(1.hour, maxValue, maxSize)

  def perMinute(maxValue: Int, maxSize: Int = defaultMaxSize): RateLimiter =
    new SimpleRateLimiter(1.minute, maxValue, maxSize)

  def merged(limiters: RateLimiter*): RateLimiter =
    (id) => limiters.foldLeft(true)(_ && _(id))
}

/**
 * A pseudo-leaky bucket; a count is incremented in a cache with a TTL.
 * If the count is incremented above the max value, the user is considered
 * rate limited, until the value expires out of cache.
 *
 * @param resolution: the TTL of the cache
 * @param maxValue: the maximum value for the counter before rate limiting
 * @param maxSize: the size of the cache
 */
class SimpleRateLimiter(resolution: Duration, maxValue: Int, maxSize: Int)
  extends RateLimiter with SimpleLogger
{
  override lazy val name = "RateLimiter[%s/%s]".format(maxValue, resolution)

  private[this] val cache =
    CacheBuilder.newBuilder.asInstanceOf[CacheBuilder[Long, AtomicInteger]]
      .maximumSize(maxSize)
      .expireAfterWrite(resolution.inMillis, TimeUnit.MILLISECONDS)
      .build[Long, AtomicInteger]()
      .asMap
      .asScala

  override def apply(user: User): Boolean = {
    val value = cache.getOrElseUpdate(user.getId, new AtomicInteger(0)).incrementAndGet()
    val limited = value > maxValue
    if (limited) log.info("rate limited %s", user.getScreenName)
    limited
  }
}