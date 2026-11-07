package dev.jobdragonquest.settings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountSettingRepository extends JpaRepository<AccountSetting, AccountSetting.Key> {
}
