package dev.boredvico.pik;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class OtpManager {
	public final static OtpManager INSTANCE = new OtpManager();
	
    private static final char[] OTP_CHARSET = "0123456789ABCDEFGHJKMNPQRTUVWXYZ".toCharArray();
	private static final int OTP_CHARSET_LEN = OTP_CHARSET.length;
	private static final int OTP_LEN = 8;

	private static final long EXPIRATION_MS = TimeUnit.MINUTES.toMillis(5);
	private final SecureRandom random = new SecureRandom();
	private final ScheduledExecutorService scheduler;

	private record AuthData(UUID playerUuid, long expiryTimestamp) {}
	private final Map<String, AuthData> activeOtps = new ConcurrentHashMap<>();
	private final Map<UUID, String> reverseOtps = new ConcurrentHashMap<>();

	private OtpManager() {
		this.scheduler = Executors.newSingleThreadScheduledExecutor();

		this.scheduler.scheduleAtFixedRate(
			this::cleanUpExpired,
			1, 
			1, 
			TimeUnit.MINUTES
		);
	}

	private String genOtp() {
		byte[] data = new byte[OTP_LEN];
		random.nextBytes(data);

		char[] otp = new char[OTP_LEN];
		for (int i = 0; i < OTP_LEN; i++) {
			otp[i] = OTP_CHARSET[(data[i] & 31) % OTP_CHARSET_LEN];
		}
		int mid = otp.length / 2;
		return new String(otp, 0, mid) + "-" + new String(otp, mid, otp.length - mid);
	}

	public String getOtp(UUID player) {
		String existingOtp = reverseOtps.get(player);
		if (existingOtp != null) {
			return existingOtp;
		}

		String otp;
		do {
			otp = genOtp();
		} while (activeOtps.containsKey(otp));

		long expiry = System.currentTimeMillis() + EXPIRATION_MS;

		AuthData data = new AuthData(player, expiry);
		activeOtps.put(otp, data);
		reverseOtps.put(player, otp);

		return otp;
	}

	public Optional<UUID> consume(String otp) {
		AuthData data = activeOtps.remove(otp);

		if (data == null) {
			return Optional.empty();
		}

		reverseOtps.remove(data.playerUuid);
		if (System.currentTimeMillis() > data.expiryTimestamp()) {
            return Optional.empty(); // OTP was valid but has expired
        }

		return Optional.of(data.playerUuid);
	}

	private void cleanUpExpired() {
		long now = System.currentTimeMillis();
        
        activeOtps.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().expiryTimestamp() < now;
            if (expired) {
				// also remove entry in reverse map
                reverseOtps.remove(entry.getValue().playerUuid());
            }
            return expired;
        });
	}

	public void shutdown() {
		scheduler.shutdownNow();
	}
}

