angular.module('openspecimen')
  .controller('ResetPasswordCtrl', function($scope, $state, $location, $translate, User, SettingUtil)  {
  
    function init() {
      if (!$location.search().token) {
        $state.go('login');
        return;
      }

      $scope.response = {};
      $scope.passwordDetail = {resetPasswordToken: $location.search().token};
      $scope.passwdCtx = {};
      loadPasswdRules();
    }

    function onResetPassword(result) {
      $scope.response.status = result.status;
      if (result.status == 'ok') {
        $scope.response.message = 'reset_password.password_updated';
      }
    }

    function loadPasswdRules() {
      SettingUtil.getSetting("auth", "password_pattern").then(
        function(setting) {
          $scope.passwdCtx.pattern = setting.value;
        }
      );

      SettingUtil.getSetting("auth", "password_rule").then(
        function(setting) {
          $scope.passwdCtx.rule = setting.value;
        }
      );
    }

    $scope.resetPassword = function() {
      User.resetPassword($scope.passwordDetail).then(onResetPassword);
    }

    init();
  });
