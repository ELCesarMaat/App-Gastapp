using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Gastapp.Models;
using Gastapp.Models.Models;
using Refit;

namespace Gastapp.Services.ApiService
{
    public interface IApiService
    {

        [Post("/User/CreateUser")]
        public Task<CreateUserResponse> CreateUser(CreateUserModel user);

        [Post("/Spendings/SyncNewSpendings")]
        public Task<bool> SyncNewSpendings(List<SpendingDto> spendings, [Authorize] string token);

        [Post("/Spendings/SyncNewCategories")]
        public Task<bool> SyncNewCategories(List<CategoryDto> spendings, [Authorize] string token);

        [Post("/User/Login")]
        public Task<AllUserData> Login(LoginModel login);

        [Get("/Spendings/GetIncomes")]
        public Task<List<IncomeType>> GetIncomes([Authorize] string token);

        [Post("/Spendings/CreateNewSpending")]
        public Task<bool> CreateNewSpending(NewSpendingDto spending, [Authorize] string token);

        [Post("/Spendings/CreateNewCategory")]
        public Task<bool> CreateNewCategory(CategoryDto spending, [Authorize] string token);

        [Post("/User/RefreshToken")]
        public Task<Token> RefreshToken([Authorize]string token);

        [Post("/User/UpdateUserPayInfo")]
        public Task<bool> UpdateUserPayInfo(UserInfoDto userPayInfo, [Authorize] string token);

        [Post("/Spendings/DeleteSpending")]
        public Task<bool> DeleteSpending(string spendingId, [Authorize] string token);
        [Post("/Spendings/SyncAllData")]
        public Task<bool> SyncAllData(SyncDataDto data, [Authorize] string token);
    }
}
